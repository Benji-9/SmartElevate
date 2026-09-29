import { useCallback, useId, useState, type ReactNode } from 'react';
import { Button } from '../components/Button';
import { OptionGroup, type Option } from '../components/OptionGroup';
import { CoreTable } from '../features/admin/CoreTable';
import { ReservationsChart } from '../features/admin/ReservationsChart';
import '../features/admin/admin.css';
import { formatMinutes, formatNumber, formatPercent, formatPeriod } from '../features/admin/format';
import { useResource } from '../hooks/useResource';
import { getAdminKpis, getBuildings } from '../services/api';
import type { AdminPeriod } from '../types/pending';

const periods: Option<AdminPeriod>[] = [
  { value: 'TODAY', label: 'Hoy' },
  { value: 'WEEK', label: 'Últimos 7 días' },
  { value: 'MONTH', label: 'Últimos 30 días' },
];

const ALL = 'ALL';

function KpiCard({ label, value, detail }: { label: string; value: string; detail: ReactNode }) {
  const labelId = useId();
  return (
    <article className="admin-card kpi" aria-labelledby={labelId}>
      <h2 id={labelId} className="kpi__label">
        {label}
      </h2>
      <p className="kpi__value">{value}</p>
      <p className="kpi__detail">{detail}</p>
    </article>
  );
}

/** Figma 09 · Panel de administración: KPIs de congestión (docs/reglas/kpis.md). */
export function AdminDashboardPage() {
  const [period, setPeriod] = useState<AdminPeriod>('TODAY');
  const [buildingId, setBuildingId] = useState(ALL);
  // Las sedes son secundarias: si fallan, queda solo "Todas las sedes".
  const buildings = useResource(getBuildings);
  const load = useCallback(
    () => getAdminKpis({ period, buildingId: buildingId === ALL ? undefined : buildingId }),
    [period, buildingId],
  );
  const kpis = useResource(load);

  const buildingOptions: Option<string>[] = [
    { value: ALL, label: 'Todas las sedes' },
    ...(buildings.data ?? []).map((b) => ({ value: b.id, label: b.name })),
  ];
  const buildingName = buildingOptions.find((o) => o.value === buildingId)?.label;
  const data = kpis.data;

  let content;
  if (kpis.error) {
    content = (
      <div role="alert" className="admin-card admin__error">
        <p>{kpis.error}</p>
        <Button variant="secondary" onClick={kpis.reload}>
          Reintentar
        </Button>
      </div>
    );
  } else if (kpis.loading || !data) {
    content = (
      <p role="status" className="page-placeholder">
        Cargando indicadores…
      </p>
    );
  } else {
    content = (
      <>
        <div className="kpis">
          <KpiCard
            label="Espera promedio"
            value={formatMinutes(data.avgWaitSeconds)}
            detail={`${formatPercent(data.wait5To10Percent)} espera 5–10 min (línea base: ${formatPercent(data.baselinePercent)})`}
          />
          <KpiCard
            label="Turnos reservados"
            value={formatNumber(data.reservations)}
            detail="Sin contar los cancelados"
          />
          <KpiCard
            label="Check-ins realizados"
            value={formatPercent(data.checkInPercent)}
            detail={`${formatNumber(data.checkIns)} de ${formatNumber(data.reservations)} turnos`}
          />
          <KpiCard
            label="Ocupación promedio"
            value={`${formatNumber(data.avgOccupancy)} / ${data.capacity}`}
            detail="Personas por salida"
          />
        </div>
        {data.reservations === 0 ? (
          <p className="admin-card page-placeholder">No hubo reservas en este período.</p>
        ) : (
          <div className="admin__panels">
            <ReservationsChart data={data.reservationsByHour} />
            <CoreTable cores={data.cores} />
          </div>
        )}
      </>
    );
  }

  return (
    <>
      <header className="admin__header">
        <div>
          <h1>Congestión y uso de ascensores</h1>
          <p className="admin__context">
            {data ? `${formatPeriod(data.from, data.to)} · ` : ''}
            {buildingName}
          </p>
        </div>
        <div className="admin__filters">
          <OptionGroup
            label="Período"
            name="period"
            options={periods}
            value={period}
            onChange={setPeriod}
            hideLabel
          />
          <OptionGroup
            label="Sede"
            name="building"
            options={buildingOptions}
            value={buildingId}
            onChange={setBuildingId}
            hideLabel
          />
        </div>
      </header>
      {content}
    </>
  );
}
