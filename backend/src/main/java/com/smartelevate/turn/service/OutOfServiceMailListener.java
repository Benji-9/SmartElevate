package com.smartelevate.turn.service;

import com.smartelevate.turn.service.ElevatorOutageService.OutageCancellations;
import com.smartelevate.turn.service.TurnMailService.OutOfServiceNotice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda los avisos de ascensor fuera de servicio después del commit (#134, #221): un rollback no deja mails ya
 * enviados y un SMTP lento no retiene la transacción. Un mail que falla no frena a los demás ni deshace nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutOfServiceMailListener {

    private final TurnMailService mails;

    // ponytail: sincrónico en el hilo del pedido del admin (AFTER_COMMIT es el default); si son cientos de mails y
    // el SMTP es lento, pasarlo a @Async.
    @TransactionalEventListener
    public void onCancellations(OutageCancellations event) {
        for (OutOfServiceNotice notice : event.notices()) {
            try {
                mails.sendElevatorOutOfService(notice);
            } catch (RuntimeException e) {
                // Sin la excepción entera: MailSendException trae el email del destinatario.
                log.error("No se pudo mandar el aviso de ascensor fuera de servicio: {}", e.getClass().getName());
            }
        }
    }
}
