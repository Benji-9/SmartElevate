import { Route, Routes } from 'react-router';
import { AdminLayout } from './components/AdminLayout';
import { ScreenLayout, TabLayout } from './components/Layouts';
import { RequireSession } from './features/auth/RequireSession';
import { SessionProvider } from './features/auth/SessionProvider';
import { DeviceStage } from './features/view-mode/DeviceStage';
import { ViewModeProvider } from './features/view-mode/ViewModeProvider';
import { useDemoMode } from './hooks/useDemoMode';
import { AdminDashboardPage } from './pages/AdminDashboardPage';
import { AdminKioskSimulatorPage } from './pages/AdminKioskSimulatorPage';
import { AdminElevatorsPage } from './pages/AdminElevatorsPage';
import { AdminKiosksPage } from './pages/AdminKiosksPage';
import { AdminPriorityPage } from './pages/AdminPriorityPage';
import { KioskPage } from './pages/KioskPage';
import { CheckInPage } from './pages/CheckInPage';
import { CheckInSuccessPage } from './pages/CheckInSuccessPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { MyTripsPage } from './pages/MyTripsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { PrivacyPage } from './pages/PrivacyPage';
import { ProfilePage } from './pages/ProfilePage';
import { RegisterPage } from './pages/RegisterPage';
import { ReserveTurnPage } from './pages/ReserveTurnPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { TermsPage } from './pages/TermsPage';
import { TurnPage } from './pages/TurnPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';

/**
 * Rutas de la app (una por frame del Figma). El router (Browser/Memory) lo provee quien la monta.
 * Todo es privado salvo login, registro, recuperar contraseña, legales y 404; `/admin` además pide rol ADMIN según `/users/me`.
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
              </Route>
            </Route>
            <Route element={<RequireSession role="ADMIN" />}>
              <Route path="admin" element={<AdminLayout />}>
                <Route index element={<AdminDashboardPage />} />
                <Route path="prioritarios" element={<AdminPriorityPage />} />
                <Route path="kioscos" element={<AdminKiosksPage />} />
                <Route path="ascensores" element={<AdminElevatorsPage />} />
                <Route path="simulador" element={<AdminKioskSimulatorPage />} />
              </Route>
            </Route>
            {/* Tablet del hall: sin sesión de usuario, con la credencial del kiosco (§10). */}
            <Route path="qr/:ascensor" element={<KioskPage />} />
            <Route element={<ScreenLayout />}>
              <Route path="login" element={<LoginPage />} />
              <Route path="registro" element={<RegisterPage />} />
              <Route path="verificar-email" element={<VerifyEmailPage />} />
              <Route path="recuperar" element={<ForgotPasswordPage />} />
              <Route path="recuperar/nueva" element={<ResetPasswordPage />} />
              <Route path="privacidad" element={<PrivacyPage />} />
              <Route path="terminos" element={<TermsPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Routes>
        </SessionProvider>
      </DeviceStage>
    </ViewModeProvider>
  );
}
