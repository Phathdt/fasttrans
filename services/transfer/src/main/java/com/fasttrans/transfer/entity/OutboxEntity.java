package com.fasttrans.transfer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

// Transactional Outbox: written in the same transaction as the business data, then published later by the relay.
@Entity
@Table(name = "outbox")
public class OutboxEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "msg_key", nullable = false)
    private String msgKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private String status;   // PENDING|SENT

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    protected OutboxEntity() {
    }

    public static OutboxEntity pending(UUID id, UUID aggregateId, String topic, String msgKey, String payload) {
        OutboxEntity o = new OutboxEntity();
        o.id = id;
        o.aggregateId = aggregateId;
        o.topic = topic;
        o.msgKey = msgKey;
        o.payload = payload;
        o.status = "PENDING";
        o.createdAt = OffsetDateTime.now();
        return o;
    }

    public void markSent() {
        this.status = "SENT";
        this.sentAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMsgKey() {
        return msgKey;
    }

    public String getPayload() {
        return payload;
    }
}
