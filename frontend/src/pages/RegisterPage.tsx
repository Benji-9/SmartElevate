import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { Button } from '../components/Button';
import { Input } from '../components/Input';
import { OptionGroup, type Option } from '../components/OptionGroup';
import { ScreenHeader } from '../components/ScreenHeader';
import { validateUadeEmail } from '../features/auth/validation';
import { ApiError, register } from '../services/api';
import type { DeclaredUserType, RegisterRequest } from '../types/pending';
import './AuthPages.css';

const FIELDS = ['fullName', 'email', 'legajo', 'password'] as const;
type Field = (typeof FIELDS)[number];
type Errors = Partial<Record<Field, string>>;

const dot = (color: string) => (
  <span className="user-type-dot" style={{ background: color }} aria-hidden="true" />
);

const userTypes: Option<DeclaredUserType>[] = [
  { value: 'STUDENT', label: 'Estudiante', adornment: dot('var(--User-Alumnos)') },
  { value: 'TEACHER', label: 'Docente', adornment: dot('var(--User-Docentes)') },
  { value: 'STAFF', label: 'Personal', adornment: dot('var(--color-border-strong)') },
];

function validate(form: RegisterRequest): Errors {
  return {
    fullName: form.fullName.trim() ? undefined : 'Ingresá tu nombre y apellido.',
    email: validateUadeEmail(form.email),
    legajo: !form.legajo.trim()
      ? 'Ingresá tu legajo.'
      : /^\d+$/.test(form.legajo.trim())
        ? undefined
        : 'El legajo tiene que ser numérico.',
    password: form.password ? undefined : 'Elegí una contraseña.',
  };
}

export function RegisterPage() {
  const [form, setForm] = useState<RegisterRequest>({
    fullName: '',
    email: '',
    legajo: '',
    password: '',
    declaredUserType: 'STUDENT',
  });
  const [errors, setErrors] = useState<Errors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

  const bind = (field: Field) => ({
    value: form[field],
    error: errors[field],
    onChange: (e: { target: { value: string } }) => setForm({ ...form, [field]: e.target.value }),
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = validate(form);
    setErrors(next);
    setServerError(null);
    if (Object.values(next).some(Boolean)) return;

    setSubmitting(true);
    const payload = {
      ...form,
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      legajo: form.legajo.trim(),
    };
    try {
      await register(payload);
      setRegisteredEmail(payload.email);
    } catch (error) {
      if (!(error instanceof ApiError)) {
        setServerError('No pudimos conectarnos. Probá de nuevo.');
      } else {
        // Email o legajo ya registrados: el error va al lado del campo.
        const fieldErrors: Errors = Object.fromEntries(
          error.violations
            .filter((v) => (FIELDS as readonly string[]).includes(v.field))
            .map((v) => [v.field, v.message]),
        );
        setErrors(fieldErrors);
        if (!Object.keys(fieldErrors).length) setServerError(error.message);
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (registeredEmail) {
    return (
      <>
        <ScreenHeader title="Crear cuenta" backTo="/login" />
        <div role="status" className="auth">
          <h2>Revisá tu email</h2>
          <p className="auth__lead">
            Te mandamos un link a <strong>{registeredEmail}</strong> para verificar tu cuenta.
            Después de verificarla vas a poder ingresar.
          </p>
          <Link to="/login">Ir a ingresar</Link>
        </div>
      </>
    );
  }

  return (
    <>
      <ScreenHeader title="Crear cuenta" backTo="/login" />
      <p className="auth__lead">Usá tu email institucional y tu legajo.</p>

      <form className="auth__form" noValidate onSubmit={handleSubmit}>
        <Input
          label="Nombre y apellido"
          autoComplete="name"
          placeholder="Juana Martínez"
          {...bind('fullName')}
        />
        <Input
          label="Email institucional"
          type="email"
          autoComplete="email"
          placeholder="nombre@uade.edu.ar"
          {...bind('email')}
        />
        <Input
          label="Legajo"
          inputMode="numeric"
          autoComplete="off"
          placeholder="Ej: 1234567"
          {...bind('legajo')}
        />
        <Input
          label="Contraseña"
          type="password"
          autoComplete="new-password"
          placeholder="••••••••"
          {...bind('password')}
        />

        <OptionGroup
          label="Tipo de usuario"
          name="declared-user-type"
          options={userTypes}
          value={form.declaredUserType}
          onChange={(declaredUserType) => setForm({ ...form, declaredUserType })}
          layout="grid"
          columns={3}
        />
        <p className="auth__lead">
          Es informativo: no te da prioridad. Si elegís Docente, un administrador lo valida.
        </p>

        <section className="auth__info" aria-labelledby="reduced-mobility-title">
          <h2 id="reduced-mobility-title" className="auth__info-title">
            ¿Tenés movilidad reducida?
          </h2>
          <p>
            Después de crear tu cuenta podés pedir acceso prioritario desde tu perfil, adjuntando un
            certificado.
          </p>
        </section>

        {serverError && (
          <p role="alert" className="auth__alert">
            {serverError}
          </p>
        )}
        <Button type="submit" loading={submitting} loadingLabel="Creando cuenta…">
          Crear cuenta
        </Button>
      </form>
    </>
  );
}
