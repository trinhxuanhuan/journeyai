#!/bin/bash

set -e

for DB in auth_service_db user_service_db booking_service_db payment_service_db notification_service_db; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    SELECT 'CREATE DATABASE $DB' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB')\gexec
EOSQL
  echo "Đã đảm bảo database '$DB' tồn tại."
done
