package com.example.wallet.common;

import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

/**
 * Base for entities whose UUID is assigned by the application (UuidV7), not the database.
 *
 * <p>WHY Persistable: Spring Data decides between persist (INSERT) and merge (SELECT, then
 * INSERT or UPDATE) by checking whether the ID is null. Our IDs are never null, so without
 * this every save() of a new entity would first run a pointless SELECT.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    protected AssignedIdEntity() {
        // for JPA
    }

    protected AssignedIdEntity(UUID id) {
        this.id = Objects.requireNonNull(id);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof AssignedIdEntity other
                && getClass() == other.getClass()
                && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
