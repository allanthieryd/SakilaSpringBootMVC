package sakila.demo.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import org.hibernate.stat.Statistics;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Instrumentation base de donnees (KPI section 4 du document) :
 *  - sakila_sql_execution_seconds{type,result} : duree / nombre / erreurs SQL
 *  - sakila_transaction_seconds{result}        : duree / nombre de transactions
 * Les metriques du pool viennent de HikariCP, celles d'Hibernate de generate_statistics.
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseMetricsConfig {

    /** Enveloppe la DataSource dans un proxy qui chronometre chaque requete SQL. */
    @Bean
    static BeanPostProcessor sqlTimingDataSourcePostProcessor(ObjectProvider<MeterRegistry> registry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource ds && !(bean instanceof ProxyDataSource)) {
                    return ProxyDataSourceBuilder.create(ds)
                            .name(beanName)
                            .listener(new SqlTimerListener(registry))
                            .build();
                }
                return bean;
            }
        };
    }

    /** Expose les Hibernate Statistics (hibernate_*) : requetes, entites chargees, transactions... */
    @Bean
    MeterBinder hibernateMetrics(EntityManagerFactory entityManagerFactory) {
        return registry -> {
            Statistics st = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            counter(registry, "hibernate.query.executions", "Requetes (HQL/SQL) executees par Hibernate", st, Statistics::getQueryExecutionCount);
            counter(registry, "hibernate.statements", "Statements JDBC prepares", st, Statistics::getPrepareStatementCount);
            counter(registry, "hibernate.entities.loaded", "Entites chargees", st, Statistics::getEntityLoadCount);
            counter(registry, "hibernate.entities.fetched", "Entites recuperees (fetch)", st, Statistics::getEntityFetchCount);
            counter(registry, "hibernate.entities.inserted", "Entites inserees", st, Statistics::getEntityInsertCount);
            counter(registry, "hibernate.entities.updated", "Entites mises a jour", st, Statistics::getEntityUpdateCount);
            counter(registry, "hibernate.entities.deleted", "Entites supprimees", st, Statistics::getEntityDeleteCount);
            counter(registry, "hibernate.collections.loaded", "Collections chargees", st, Statistics::getCollectionLoadCount);
            counter(registry, "hibernate.transactions", "Transactions Hibernate", st, Statistics::getTransactionCount);
            counter(registry, "hibernate.sessions.opened", "Sessions ouvertes", st, Statistics::getSessionOpenCount);
            Gauge.builder("hibernate.query.execution.max", st, Statistics::getQueryExecutionMaxTime)
                    .description("Duree de la requete la plus lente (ms)").baseUnit("milliseconds").register(registry);
        };
    }

    private static void counter(MeterRegistry registry, String name, String description, Statistics st,
                                java.util.function.ToDoubleFunction<Statistics> fn) {
        FunctionCounter.builder(name, st, fn).description(description).register(registry);
    }

    /** Ajoute un listener de duree sur le gestionnaire de transactions. */
    @Bean
    static BeanPostProcessor transactionTimingPostProcessor(ObjectProvider<MeterRegistry> registry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof AbstractPlatformTransactionManager tm) {
                    tm.addListener(new TransactionTimerListener(registry));
                }
                return bean;
            }
        };
    }

    static class SqlTimerListener implements QueryExecutionListener {
        private final ObjectProvider<MeterRegistry> registry;

        SqlTimerListener(ObjectProvider<MeterRegistry> registry) {
            this.registry = registry;
        }

        @Override
        public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        }

        @Override
        public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
            MeterRegistry reg = registry.getIfAvailable();
            if (reg == null) {
                return;
            }
            String type = queryInfoList.isEmpty() ? "other" : sqlType(queryInfoList.get(0).getQuery());
            Timer.builder("sakila.sql.execution")
                    .description("Duree d'execution des requetes SQL")
                    .tags("type", type, "result", execInfo.isSuccess() ? "success" : "error")
                    .publishPercentileHistogram()
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(reg)
                    .record(execInfo.getElapsedTime(), TimeUnit.MILLISECONDS);
        }

        private static String sqlType(String sql) {
            String s = sql == null ? "" : sql.stripLeading().toLowerCase();
            for (String t : new String[]{"select", "insert", "update", "delete"}) {
                if (s.startsWith(t)) {
                    return t;
                }
            }
            return "other";
        }
    }

    static class TransactionTimerListener implements TransactionExecutionListener {
        private final ObjectProvider<MeterRegistry> registry;
        private final Map<TransactionExecution, Timer.Sample> samples = new ConcurrentHashMap<>();

        TransactionTimerListener(ObjectProvider<MeterRegistry> registry) {
            this.registry = registry;
        }

        @Override
        public void afterBegin(TransactionExecution tx, Throwable beginFailure) {
            MeterRegistry reg = registry.getIfAvailable();
            if (reg != null && beginFailure == null) {
                samples.put(tx, Timer.start(reg));
            }
        }

        @Override
        public void afterCommit(TransactionExecution tx, Throwable commitFailure) {
            stop(tx, commitFailure == null ? "commit" : "error");
        }

        @Override
        public void afterRollback(TransactionExecution tx, Throwable rollbackFailure) {
            stop(tx, "rollback");
        }

        private void stop(TransactionExecution tx, String result) {
            Timer.Sample sample = samples.remove(tx);
            MeterRegistry reg = registry.getIfAvailable();
            if (sample != null && reg != null) {
                sample.stop(Timer.builder("sakila.transaction")
                        .description("Duree des transactions")
                        .tags("result", result, "readonly", String.valueOf(tx.isReadOnly()))
                        .publishPercentileHistogram()
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(reg));
            }
        }
    }
}
