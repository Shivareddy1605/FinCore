
package com.fincore.api;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import com.fincore.api.exception.CustomerExceptionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerExceptionHandlerTests {

    private final CustomerExceptionHandler handler =
            new CustomerExceptionHandler();

    @Test
    void duplicateCustomerEmailReturns409() {

        SQLException sqlException = new SQLException(
                "duplicate key value violates unique constraint "
                        + "\"uk_customers_email\"",
                "23505");

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Customer email constraint violation",
                        sqlException);

        var response = handler.handleDataIntegrityViolation(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Email already registered", response.getBody());
    }

    @Test
    void unrelatedDatabaseErrorIsRethrown() {

        SQLException sqlException = new SQLException(
                "Some other database constraint violation",
                "23505");

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Other constraint violation",
                        sqlException);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> handler.handleDataIntegrityViolation(exception));
    }
}
