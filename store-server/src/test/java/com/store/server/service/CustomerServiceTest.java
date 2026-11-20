package com.store.server.service;

import com.store.server.TestSupport.RecordingEventBus;
import com.store.server.TestSupport.RecordingLogger;
import com.store.server.dao.json.JsonCustomerDao;
import com.store.server.model.customer.CustomerFactory;
import com.store.server.model.customer.CustomerType;
import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.EventType;
import com.store.server.protocol.dto.Dtos.CustomerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CustomerServiceTest {

    private static final String ID = "314159260";

    @TempDir
    Path dir;

    private RecordingEventBus bus;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        JsonCustomerDao dao = new JsonCustomerDao(dir.resolve("customers.json"));
        dao.save(CustomerFactory.create(CustomerType.NEW, ID, "Noa", "050-1111111"));
        bus = new RecordingEventBus();
        service = new CustomerService(dao, bus, new RecordingLogger());
    }

    @Test
    void updateChangesOnlyTheGivenFieldsAndNotifiesEveryone() {
        service.update("tlv.cashier", new CustomerRequest(ID, "Noa Levi", null, null));

        assertEquals("Noa Levi", service.get(ID).getFullName());
        assertEquals("050-1111111", service.get(ID).getPhone());
        assertEquals(1, bus.count("all", EventType.CUSTOMER_UPDATED));
    }

    @Test
    void failedUpdateChangesNothing() {
        // Valid name, invalid phone: the whole update must be rejected.
        DomainException e = assertThrows(DomainException.class,
                () -> service.update("tlv.cashier", new CustomerRequest(ID, "Other Name", "not-a-phone", null)));
        assertEquals(ErrorCode.BAD_REQUEST, e.code());

        assertEquals("Noa", service.get(ID).getFullName());
        assertEquals(0, bus.count("all", EventType.CUSTOMER_UPDATED));
    }
}
