import { useCallback, useId, useState, type ReactNode } from 'react';
import { Button } from '../components/Button';
import { OptionGroup, type Option } from '../components/OptionGroup';
import { CoreTable } from '../features/admin/CoreTable';
import { ReservationsChart } from '../features/admin/ReservationsChart';
import '../features/admin/admin.css';
import {
  formatMinutes,
  formatNumber,
  formatPercent,
  formatPeriod,
  formatShift,
} from '../features/admin/format';
import { useResource } from '../hooks/useResource';
import { getAdminKpis, getAdminShifts, getBuildings } from '../services/api';
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
      <h2 id={labelId} className="text-label kpi__label">
        {label}
      </h2>
      <p className="text-kpi">{value}</p>
      <p className="text-caption-strong kpi__detail">{detail}</p>
    </article>
  );
}

/** Figma 09 · Panel de administración: KPIs de congestión (docs/reglas/kpis.md). */
export function AdminDashboardPage() {
  const [period, setPeriod] = useState<AdminPeriod>('TODAY');
  const [buildingId, setBuildingId] = useState(ALL);
  const [shiftId, setShiftId] = useState(ALL);
  // Sedes y turnos son secundarios: si fallan, queda solo "Todas las sedes" / "Todo el día".
  const buildings = useResource(getBuildings);
  const shifts = useResource(getAdminShifts);
  const load = useCallback(
    () =>
      getAdminKpis({
        period,
        buildingId: buildingId === ALL ? undefined : buildingId,
        shiftId: shiftId === ALL ? undefined : shiftId,
      }),
    [period, buildingId, shiftId],
  );
  const kpis = useResource(load);

  const buildingOptions: Option<string>[] = [
    { value: ALL, label: 'Todas las sedes' },
    ...(buildings.data ?? []).map((b) => ({ value: b.id, label: b.name })),
  ];
  const buildingName = buildingOptions.find((o) => o.value === buildingId)?.label;
  const shiftOptions: Option<string>[] = [
    { value: ALL, label: 'Todo el día' },
    ...(shifts.data ?? []).map((s) => ({ value: s.id, label: s.name })),
  ];
  const shift = shifts.data?.find((s) => s.id === shiftId);
  const data = kpis.data;
  const context = [
    data && formatPeriod(data.from, data.to),
    buildingName,
    shift && formatShift(shift),
  ]
    .filter(Boolean)
    .join(' · ');

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
            detail={periods.find((p) => p.value === period)?.label.toLowerCase()}
          />
          <KpiCard
            label="Check-ins realizados"
            value={formatPercent(data.checkInPercent)}
            detail="de los turnos reservados"
          />
          <KpiCard
            label="Ocupación promedio"
            value={`${formatNumber(data.avgOccupancy)} / ${data.capacity}`}
            detail="personas por franja"
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
          <h1 className="text-title-lg">Congestión y uso de ascensores</h1>
          <p className="admin__context">{context}</p>
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
          <OptionGroup
            label="Turno de cursada"
            name="shift"
            options={shiftOptions}
            value={shiftId}
            onChange={setShiftId}
            hideLabel
          />
        </div>
      </header>
      {content}
    </>
  );
}
