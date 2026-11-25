package com.example.customer.repository;

import com.example.customer.entity.Contact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    List<Contact> findByClientId(UUID clientId);
}
