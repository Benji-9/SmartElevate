package com.smartelevate.common.mail;

/** Mínimo para mandar un mail de texto. Lo usan la verificación de email (#119) y el reset de contraseña (#138). */
public interface EmailSender {

    void send(String to, String subject, String body);
}
