import { Input } from '../../components/Input';
import { toUadeUser, UADE_DOMAIN } from './validation';

type Props = {
  /** Solo el usuario, sin el dominio. */
  value: string;
  onChange: (user: string) => void;
  error?: string;
};

/** "Email institucional": se escribe el usuario y el dominio queda fijo a la derecha. */
export function UadeEmailInput({ value, onChange, error }: Props) {
  return (
    <Input
      label="Email institucional"
      suffix={UADE_DOMAIN}
      autoComplete="username"
      autoCapitalize="none"
      autoCorrect="off"
      spellCheck={false}
      placeholder="jmartinez"
      value={value}
      onChange={(e) => onChange(toUadeUser(e.target.value))}
      error={error}
    />
  );
}
