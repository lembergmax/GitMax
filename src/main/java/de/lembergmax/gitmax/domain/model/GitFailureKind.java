package de.lembergmax.gitmax.domain.model;

/**
 * Art eines Git-Fehlers, grob genug für eine klare Handlungsanweisung an den Nutzer.
 */
public enum GitFailureKind {

    /** Anmeldung abgelehnt: Token fehlt, ist abgelaufen oder hat nicht das nötige Recht. */
    AUTH,
    /** Das Repo gibt es unter dieser Adresse nicht (oder der Token darf es nicht sehen). */
    NOT_FOUND,
    /** Keine Verbindung, Zeitüberschreitung, TLS-Fehler. */
    NETWORK,
    /** Das Remote hat neuere Commits: erst aktualisieren. */
    NOT_FAST_FORWARD,
    /** Das Remote hat die Änderung abgelehnt, z. B. wegen eines geschützten Branches. */
    REJECTED,
    /** Konflikte beim Zusammenführen. */
    CONFLICT,
    /** Lokale Änderungen würden überschrieben. */
    DIRTY_TREE,
    /** Der Branch hat keinen Upstream, mit dem sich abgleichen ließe. */
    NO_UPSTREAM,
    /** Der Ordner ist kein (lesbares) Git-Repo. */
    NOT_A_REPO,
    /** Es gibt nichts zu committen. */
    NOTHING_TO_COMMIT,
    /** Der Zielordner existiert schon und ist nicht leer. */
    ALREADY_EXISTS,
    /** Kein Speicherplatz mehr. */
    DISK_FULL,
    /** Ein Dateiname im Repo ist auf dem Telefonspeicher nicht erlaubt. */
    INVALID_PATH,
    /** Ein Name (Branch, Tag, Remote) ist nach den Git-Regeln ungültig. */
    INVALID_NAME,
    /** Der Branch ist noch nicht in den aktuellen Stand eingegangen; Löschen verliert Commits. */
    NOT_MERGED,
    /** Der aktuell ausgecheckte Branch lässt sich so nicht ändern. */
    CURRENT_BRANCH,
    /** Ein SSH-Server ist noch nicht bekannt: sein Fingerabdruck muss bestätigt werden. */
    HOST_KEY_UNKNOWN,
    /** Der Schlüssel eines bekannten SSH-Servers hat sich geändert; das wird nie stillschweigend akzeptiert. */
    HOST_KEY_CHANGED,
    /** Für SSH ist noch kein Schlüssel eingerichtet. */
    SSH_NO_KEY,
    /** Der Server hat keinen der angebotenen SSH-Schlüssel akzeptiert. */
    SSH_REJECTED,
    /** „Nur im WLAN“ ist an, und das Gerät hängt an einem gemessenen Netz (mobile Daten). */
    METERED_NETWORK,
    /** Der Nutzer hat abgebrochen. */
    CANCELLED,
    /** Nicht zuordenbar; die technische Meldung steht in der Ausnahme. */
    UNKNOWN
}
