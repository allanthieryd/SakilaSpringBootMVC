package sakila.demo.repository;

import sakila.demo.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query(value = "SELECT SUM(p.amount) FROM payment p WHERE p.payment_date BETWEEN :startDate AND :endDate", nativeQuery = true)
    Double findTotalPaymentBetweenDates(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
