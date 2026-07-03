package com.fasttrans.account.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox — publishes transfer.result to Kafka.
 * OutboxRelay polls this table every 1s, sends, and marks it SENT.
 */
@Entity
@Table(name = "outbox")
public class OutboxEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** transferId — partition key for the Kafka message. */
    @Column(name = "aggregate_id", nullable = false, columnDefinition = "uuid")
    private UUID aggregateId;

    @Column(nullable = false, length = 50)
    private String topic;

    /** Partition key sent to Kafka. */
    @Column(name = "msg_key", nullable = false, length = 100)
    private String msgKey;

    /** Event JSON stored as jsonb. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    /** PENDING or SENT. */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** Factory method — creates a new record in the PENDING state. */
    public static OutboxEntity pending(UUID aggregateId, String topic, String msgKey, String payload) {
        OutboxEntity e = new OutboxEntity();
        e.id = UUID.randomUUID();
        e.aggregateId = aggregateId;
        e.topic = topic;
        e.msgKey = msgKey;
        e.payload = payload;
        e.status = "PENDING";
        e.createdAt = Instant.now();
        return e;
    }

    /** Marks it as successfully published. */
    public void markSent() {
        this.status = "SENT";
        this.sentAt = Instant.now();
    }

    // --- getters / setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getMsgKey() { return msgKey; }
    public void setMsgKey(String msgKey) { this.msgKey = msgKey; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
