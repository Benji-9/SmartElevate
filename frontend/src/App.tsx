import { Route, Routes } from 'react-router';
import { AdminLayout } from './components/AdminLayout';
import { ScreenLayout, TabLayout } from './components/Layouts';
import { RequireSession } from './features/auth/RequireSession';
import { SessionProvider } from './features/auth/SessionProvider';
import { DeviceStage } from './features/view-mode/DeviceStage';
import { ViewModeProvider } from './features/view-mode/ViewModeProvider';
import { useDemoMode } from './hooks/useDemoMode';
import { AdminDashboardPage } from './pages/AdminDashboardPage';
import { CheckInPage } from './pages/CheckInPage';
import { CheckInSuccessPage } from './pages/CheckInSuccessPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { MyTripsPage } from './pages/MyTripsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { ProfilePage } from './pages/ProfilePage';
import { RegisterPage } from './pages/RegisterPage';
import { ReserveTurnPage } from './pages/ReserveTurnPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { TurnPage } from './pages/TurnPage';

/**
 * Rutas de la app (una por frame del Figma). El router (Browser/Memory) lo provee quien la monta.
 * Todo es privado salvo login, registro, recuperar contraseña y 404; `/admin` además pide rol ADMIN según `/me`.
 */
export function App() {
  // Guarda `?demo=1` apenas se entra, aunque la primera pantalla no lo use.
  useDemoMode();
  return (
    <ViewModeProvider>
      <DeviceStage>
        <SessionProvider>
          <Routes>
            <Route element={<RequireSession />}>
              <Route element={<TabLayout />}>
                <Route index element={<HomePage />} />
                <Route path="perfil" element={<ProfilePage />} />
              </Route>
              {/* Sin navegación inferior; en Pantalla llevan TopBar (VIEW-MODES.md §5). */}
              <Route element={<ScreenLayout topBar />}>
                <Route path="reservar" element={<ReserveTurnPage />} />
                <Route path="perfil/viajes" element={<MyTripsPage />} />
                <Route path="perfil/notificaciones" element={<NotificationsPage />} />
              </Route>
              {/* Confirmaciones: columna de 480 sin TopBar también en Pantalla (§6). */}
              <Route element={<ScreenLayout />}>
                <Route path="turno/:id" element={<TurnPage />} />
                <Route path="check-in/ok" element={<CheckInSuccessPage />} />
              </Route>
              <Route element={<ScreenLayout bleed topBar />}>
                <Route path="check-in" element={<CheckInPage />} />
                <Route path="check-in/codigo" element={<CheckInPage manual />} />
              </Route>
            </Route>
            <Route element={<RequireSession role="ADMIN" />}>
              <Route path="admin" element={<AdminLayout />}>
                <Route index element={<AdminDashboardPage />} />
              </Route>
            </Route>
            <Route element={<ScreenLayout />}>
              <Route path="login" element={<LoginPage />} />
              <Route path="registro" element={<RegisterPage />} />
              <Route path="recuperar" element={<ForgotPasswordPage />} />
              <Route path="recuperar/nueva" element={<ResetPasswordPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Routes>
        </SessionProvider>
      </DeviceStage>
    </ViewModeProvider>
  );
}
