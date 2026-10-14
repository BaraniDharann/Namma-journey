package com.travelplatform.idempotency;

import java.util.HashMap;
import java.util.Map;

/** Test double with the same contract as {@link JdbcIdempotencyStore}, minus the database. */
class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Row(String requestHash, StoredResponse response) {
    }

    private final Map<String, Row> rows = new HashMap<>();

    private static String id(String scope, String key) {
        return scope + "#" + key;
    }

    @Override
    public synchronized Claim claim(String scope, String key, String requestHash) {
        Row row = rows.get(id(scope, key));
        if (row == null) {
            rows.put(id(scope, key), new Row(requestHash, null));
            return Claim.acquired();
        }
        if (!row.requestHash().equals(requestHash)) {
            return Claim.mismatch();
        }
        return row.response() == null ? Claim.inProgress() : Claim.replay(row.response());
    }

    @Override
    public synchronized void complete(String scope, String key, StoredResponse response) {
        Row row = rows.get(id(scope, key));
        rows.put(id(scope, key), new Row(row.requestHash(), response));
    }

    @Override
    public synchronized void release(String scope, String key) {
        rows.remove(id(scope, key));
    }
}
