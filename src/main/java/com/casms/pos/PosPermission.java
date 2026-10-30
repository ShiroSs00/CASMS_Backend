package com.casms.pos;

/** Scoped operations, never catalogue administration or global role grants. */
public enum PosPermission {
    SERVICE_READ(ResourceType.SERVICE,"READ"),
    SERVICE_REQUEST_READ(ResourceType.SERVICE,"REQUEST"),
    SERVICE_REQUEST_ASSIGN(ResourceType.SERVICE,"REQUEST"),
    SERVICE_REQUEST_EXECUTE(ResourceType.SERVICE,"REQUEST"),
    SERVICE_SALE(ResourceType.SERVICE,"USAGE"),
    ACTIVITY_READ(ResourceType.ACTIVITY,"READ"),
    ACTIVITY_REGISTRATION(ResourceType.ACTIVITY,"REGISTRATION"),
    ACTIVITY_ATTENDANCE(ResourceType.ACTIVITY,"ATTENDANCE"),
    ACTIVITY_LIFECYCLE(ResourceType.ACTIVITY,"LIFECYCLE");

    public enum ResourceType { SERVICE, ACTIVITY }
    private final ResourceType resourceType;
    private final String category;
    PosPermission(ResourceType resourceType,String category) {this.resourceType=resourceType;this.category=category;}
    public ResourceType resourceType() {return resourceType;}
    public String category() {return category;}
    public static java.util.Set<PosPermission> forResource(ResourceType type) {
        return java.util.Arrays.stream(values()).filter(p->p.resourceType==type)
                .collect(java.util.stream.Collectors.toCollection(()->java.util.EnumSet.noneOf(PosPermission.class)));
    }
}
