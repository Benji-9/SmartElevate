package com.smartelevate.turn.service;

import com.smartelevate.common.config.AsyncConfig;
import com.smartelevate.turn.service.ElevatorOutageService.OutageCancellations;
import com.smartelevate.turn.service.TurnMailService.OutOfServiceNotice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda los avisos de ascensor fuera de servicio después del commit y fuera del hilo del pedido (#134, #221): un
 * rollback no deja mails ya enviados, y como corre en el executor acotado {@code mailExecutor} ni la transacción, ni la
 * conexión JDBC ni el request del admin esperan al SMTP. Un mail que falla no frena a los demás ni deshace nada, y el
 * éxito del POST no depende del envío.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutOfServiceMailListener {

    private final TurnMailService mails;

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener
    public void onCancellations(OutageCancellations event) {
        int failed = 0;
        for (OutOfServiceNotice notice : event.notices()) {
            try {
                mails.sendElevatorOutOfService(notice);
            } catch (RuntimeException e) {
                failed++;
            }
        }
        if (failed > 0) {
            // Solo el total: la excepción de mail trae el email del destinatario.
            log.error("No se pudieron mandar {} de {} avisos de ascensor fuera de servicio.", failed,
                    event.notices().size());
        }
    }
}
