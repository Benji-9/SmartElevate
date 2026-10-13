package com.smartelevate.user.service;

import java.util.List;
import lombok.Getter;

/** El email o el legajo ya tienen cuenta. {@code fields} dice cuáles, con los nombres del pedido de registro. */
@Getter
public class DuplicateUserException extends RuntimeException {

    private final List<String> fields;

    public DuplicateUserException(List<String> fields) {
        super("Ya existe una cuenta con el mismo " + String.join(" y ", fields));
        this.fields = List.copyOf(fields);
    }
}
