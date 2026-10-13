package com.smartelevate.user.model;

/**
 * Lo que la persona dice ser al registrarse (#32). Es un dato informativo: no otorga rol ni prioridad.
 * Un docente declarado queda en la cola del panel de administración hasta que un ADMIN lo valida.
 */
public enum DeclaredUserType {
    STUDENT,
    TEACHER
}
