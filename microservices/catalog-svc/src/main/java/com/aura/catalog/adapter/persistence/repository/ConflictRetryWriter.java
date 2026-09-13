package com.aura.catalog.adapter.persistence.repository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Isolates a speculative insert in its own transaction so a lost race doesn't poison the caller's.
 *
 * {@code CatalogService}'s parallel upserts mean two concurrent searches (or even two branches of
 * the same search — e.g. two tracks from the same album) can both decide the same provider
 * reference is new and both try to insert it. One wins; the other's insert fails on the unique
 * index. Postgres aborts the *current* transaction the instant any statement fails in it — so the
 * loser's repository must run that insert attempt in its own transaction (this class, via
 * {@code REQUIRES_NEW}) to fail and roll back on its own, leaving the caller's transaction free to
 * fall back to an ordinary update.
 *
 * A plain {@code @Transactional} method on the repository itself wouldn't do this: Spring's proxy
 * only applies transactional advice to calls arriving from outside the bean, and the repositories
 * call this from their own {@code upsert()} — a different bean sidesteps that self-invocation trap.
 */
@Component
class ConflictRetryWriter {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    <T> T runInNewTransaction(Supplier<T> insert) {
        return insert.get();
    }
}
