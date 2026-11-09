package com.ganzithon.teolhanol.domain.care.service;

import com.ganzithon.teolhanol.domain.safety.service.AiResponseGuard;
import java.util.function.Consumer;

/**
 * 스트리밍 응답을 문장 단위로 모아 안전 가드를 통과한 문장만 내보냅니다 (SAF-03).
 * 금지 패턴이 걸리면 그 문장과 이후 조각은 내보내지 않고 {@link #blocked()}가 true가 됩니다.
 * 패턴이 문장 경계에 걸쳐도 놓치지 않도록 이미 내보낸 글의 끝부분을 함께 검사합니다.
 */
final class SentenceGate {

    private static final String BOUNDARIES = ".!?\n";
    private static final int CONTEXT_CHARS = 20;
    private static final int MAX_PENDING_CHARS = 200;

    private final AiResponseGuard guard;
    private final Consumer<String> out;
    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder sent = new StringBuilder();
    private boolean blocked;

    SentenceGate(AiResponseGuard guard, Consumer<String> out) {
        this.guard = guard;
        this.out = out;
    }

    void accept(String delta) {
        if (blocked || delta == null || delta.isEmpty()) {
            return;
        }
        pending.append(delta);
        int end = lastBoundary();
        if (end < 0 && pending.length() >= MAX_PENDING_CHARS) {
            end = pending.length() - 1;
        }
        if (end >= 0) {
            release(pending.substring(0, end + 1), end + 1);
        }
    }

    /** 스트림이 끝났을 때 문장부호 없이 남은 끝부분을 처리합니다. */
    void finish() {
        if (!blocked && pending.length() > 0) {
            release(pending.toString(), pending.length());
        }
    }

    boolean blocked() {
        return blocked;
    }

    /** 지금까지 화면에 내보낸 글이 있는지 */
    boolean sentAnything() {
        return sent.length() > 0;
    }

    private int lastBoundary() {
        for (int i = pending.length() - 1; i >= 0; i--) {
            if (BOUNDARIES.indexOf(pending.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    private void release(String segment, int consumed) {
        String context = sent.substring(Math.max(0, sent.length() - CONTEXT_CHARS));
        if (guard.violates(context + segment)) {
            blocked = true;
            pending.setLength(0);
            return;
        }
        pending.delete(0, consumed);
        sent.append(segment);
        out.accept(segment);
    }
}
