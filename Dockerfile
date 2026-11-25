FROM mysql:latest

ENV MYSQL_ROOT_PASSWORD=p_ssW0rd
ENV MYSQL_DATABASSE_SAKILA=sakila

COPY my.cnf /etc/mysql/conf.d/

RUN mkdir -p /var/lib/mysql-kering

EXPOSE 3306
