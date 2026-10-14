-- Los límites de logins fallidos son parámetros de seguridad, no reglas de negocio (#225): pasan a
-- application.yml (app.login-throttle.*, con override por variable de entorno). V13 no se edita.
DELETE FROM app_config
WHERE config_key IN ('login_failure_window', 'login_max_failures_per_account', 'login_max_failures_per_ip');
