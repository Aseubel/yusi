package com.aseubel.yusi.service.lifegraph.constant;

import java.util.Locale;

/** Relation protocol used by extraction, promotion and retrieval. */
public enum LifeGraphRelationType {
    PARTNER_OF("PARTNER_OF", true, false, false),
    FAMILY_OF("FAMILY_OF", true, false, false),
    FRIEND_OF("FRIEND_OF", true, false, false),
    COLLEAGUE_OF("COLLEAGUE_OF", true, false, false),
    MENTOR_OF("MENTOR_OF", true, false, false),
    SIBLING_OF("SIBLING_OF", true, false, false),
    PARENT_OF("PARENT_OF", true, false, false),
    CHILD_OF("CHILD_OF", true, false, false),
    LIKES("LIKES", false, true, false),
    DISLIKES("DISLIKES", false, true, false),
    BOUGHT_FOR("BOUGHT_FOR", false, true, false),
    PARTICIPATED_IN("PARTICIPATED_IN", false, true, false),
    EXPERIENCED("EXPERIENCED", false, true, false),
    HAPPENED_AT("HAPPENED_AT", false, true, false),
    TRIGGERED("TRIGGERED", false, true, false),
    WORKED_AT("WORKED_AT", false, true, false),
    LIVED_AT("LIVED_AT", false, true, false),
    CARED_FOR("CARED_FOR", false, true, false),
    HAS_BIRTHDAY("HAS_BIRTHDAY", false, true, false),
    HAS_IMPORTANT_EVENT("HAS_IMPORTANT_EVENT", false, true, false),
    VISITED("VISITED", false, true, false),
    ATTENDED("ATTENDED", false, true, false),
    MENTIONED("MENTIONED", false, false, true),
    MENTIONED_IN("MENTIONED_IN", false, false, true),
    SAID("SAID", false, false, true),
    RELATED_TO("RELATED_TO", false, false, true);

    private final String code;
    private final boolean personRelation;
    private final boolean valueRelation;
    private final boolean rejectedForAutomaticGraph;

    LifeGraphRelationType(String code, boolean personRelation, boolean valueRelation,
                          boolean rejectedForAutomaticGraph) {
        this.code = code;
        this.personRelation = personRelation;
        this.valueRelation = valueRelation;
        this.rejectedForAutomaticGraph = rejectedForAutomaticGraph;
    }

    public String code() {
        return code;
    }

    public boolean isPersonRelation() {
        return personRelation;
    }

    public boolean isValueRelation() {
        return valueRelation;
    }

    public boolean isRejectedForAutomaticGraph() {
        return rejectedForAutomaticGraph;
    }

    public boolean isSupportedForAutomaticGraph() {
        return personRelation || valueRelation;
    }

    public static LifeGraphRelationType fromCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (LifeGraphRelationType relationType : values()) {
            if (relationType.code.equals(normalized)) {
                return relationType;
            }
        }
        return null;
    }
}
