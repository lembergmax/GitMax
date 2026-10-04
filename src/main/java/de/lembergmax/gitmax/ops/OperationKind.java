package de.lembergmax.gitmax.ops;

/** Art eines Vorgangs in der Warteschlange: die langlaufenden Netzwerk-Operationen. */
public enum OperationKind {

    CLONE,
    UPDATE,
    FETCH,
    PUSH,
    /** Ein neues Repo beim Anbieter anlegen, lokal einrichten und zum ersten Mal pushen. */
    CREATE
}
