-- Решения PM по итогам недели 2:
--  * заявку может отменить пользователь — отмененная заявка освобождает место (п. 4.9.7);
--  * все цены — строго в белорусских рублях (BYN), отдельная колонка валюты не нужна.

ALTER TABLE booking DROP CONSTRAINT ck_booking_status;
ALTER TABLE booking ADD CONSTRAINT ck_booking_status
    CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'REJECTED', 'CANCELLED_BY_USER'));

COMMENT ON COLUMN event_offer.price IS 'Цена в белорусских рублях (BYN)';
