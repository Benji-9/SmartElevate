import logo from '../assets/smartelevate-logo.svg';
import './AuthBrand.css';

/**
 * Marca de SmartElevate en las pantallas de cuenta (registro, recuperar y verificar). Login usa
 * la misma imagen más grande y con `alt=""`, porque ahí el `h1` ya nombra a SmartElevate.
 */
export function AuthBrand() {
  return <img className="auth-brand" src={logo} alt="SmartElevate" width={109} height={72} />;
}
