import { Route, Routes } from 'react-router';
import { Layout } from './components/Layout';
import { ElevatorStatusPage } from './pages/ElevatorStatusPage';
import { HomePage } from './pages/HomePage';
import { MyTurnsPage } from './pages/MyTurnsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { RequestTurnPage } from './pages/RequestTurnPage';

/** Rutas de la app. El router (Browser/Memory) lo provee quien la monta. */
export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="solicitar-turno" element={<RequestTurnPage />} />
        <Route path="mis-turnos" element={<MyTurnsPage />} />
        <Route path="ascensores" element={<ElevatorStatusPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
