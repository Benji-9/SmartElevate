import { useCallback, useId, useState, type ReactNode } from 'react';
import { Button } from '../components/Button';
import { Chip } from '../components/Chip';
import { OptionGroup, type Option } from '../components/OptionGroup';
import { CoreTable } from '../features/admin/CoreTable';
import { ReservationsChart } from '../features/admin/ReservationsChart';
import '../features/admin/admin.css';
import {
  formatMinutes,
  formatNumber,
  formatPercent,
  formatPeriod,
  currentShift,
  formatShift,
  NO_DATA,
} from '../features/admin/format';
import { useResource } from '../hooks/useResource';
import { getAdminKpis, getAdminShifts, getBuildings } from '../services/api';
import type { AdminPeriod } from '../types/api';

const periods: Option<AdminPeriod>[] = [
  { value: 'TODAY', label: 'Hoy' },
  { value: 'WEEK', label: 'Últimos 7 días' },
  { value: 'MONTH', label: 'Últimos 30 días' },
];

/** Con qué se compara cada período (`previous`: mismo largo, inmediatamente antes). */
const previousLabel: Record<AdminPeriod, string> = {
  TODAY: 'ayer',
  WEEK: 'los 7 días anteriores',
  MONTH: 'los 30 días anteriores',
};

const ALL = 'ALL';

/**
 * Variación contra el período anterior (#180): en % para cantidades y en puntos para los
 * porcentajes. Tono neutro: más reservas u ocupación no es mejor ni peor por sí solo.
 */
function PreviousComparison({
  current,
  previous,
  points = false,
  label,
}: {
  current: number | null;
  previous: number | null | undefined;
  points?: boolean;
  label: string;
}) {
  if (current == null || previous == null || (!points && previous === 0)) return null;
  const diff =
    Math.round((points ? current - previous : ((current - previous) / previous) * 100) * 10) / 10;
  if (diff === 0) return <Chip tone="neutral">{`Igual que ${label}`}</Chip>;
  return (
    <Chip tone="neutral">
      <span aria-hidden="true">{diff > 0 ? '▲' : '▼'}</span>
      {`${diff > 0 ? 'Subió' : 'Bajó'} ${points ? `${formatNumber(Math.abs(diff))} pts` : formatPercent(Math.abs(diff))} vs. ${label}`}
    </Chip>
  );
}

/**
 * Variación del % que espera 5–10 min contra la línea base de la encuesta (kpis.md): menos
 * es mejor. Lo dice con flecha, color y texto, para no depender solo del color.
 */
function BaselineComparison({ percent, baseline }: { percent: number; baseline: number }) {
  const diff = Math.round((percent - baseline) * 10) / 10;
  if (diff === 0) return <Chip tone="neutral">Igual que la línea base</Chip>;
  const better = diff < 0;
  return (
    <Chip tone={better ? 'baja' : 'alta'}>
      <span aria-hidden="true">{better ? '▼' : '▲'}</span>
      {`${formatNumber(Math.abs(diff))} pts · ${better ? 'mejor' : 'peor'} que la línea base`}
    </Chip>
  );
}

function KpiCard({
  label,
  value,
  detail,
  comparison,
}: {
  label: string;
  value: string;
  detail: ReactNode;
  comparison?: ReactNode;
}) {
  const labelId = useId();
  return (
    <article className="admin-card kpi" aria-labelledby={labelId}>
      <h2 id={labelId} className="text-label kpi__label">
        {label}
      </h2>
      <p className="text-kpi">{value}</p>
      <p className="text-caption-strong kpi__detail">{detail}</p>
      {comparison}
    </article>
  );
}

/** Figma 09 · Panel de administración: KPIs de congestión (docs/reglas/kpis.md). */
export function AdminDashboardPage() {
  const [period, setPeriod] = useState<AdminPeriod>('TODAY');
  const [buildingId, setBuildingId] = useState(ALL);
  const [shiftId, setShiftId] = useState(ALL);
  // Edificios y turnos son secundarios: si fallan, queda solo "Todos los edificios" / "Todo el día".
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
    { value: ALL, label: 'Todos los edificios' },
    ...(buildings.data ?? []).map((b) => ({ value: b.code, label: b.name })),
  ];
  const buildingName = buildingOptions.find((o) => o.value === buildingId)?.label;
  const shiftOptions: Option<string>[] = [
    { value: ALL, label: 'Todo el día' },
    ...(shifts.data ?? []).map((s) => ({ value: s.id, label: s.name })),
  ];
  const shift = shifts.data?.find((s) => s.id === shiftId);
  const data = kpis.data;
  const nowShift = shifts.data && currentShift(shifts.data, new Date());
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
    const { previous } = data;
    const vs = previousLabel[period];
    content = (
      <>
        <div className="kpis">
          <KpiCard
            label="Espera promedio"
            value={data.avgWaitSeconds == null ? NO_DATA : formatMinutes(data.avgWaitSeconds)}
            detail={
              data.wait5To10Percent == null
                ? `Sin datos de la encuesta de espera (línea base: ${formatPercent(data.baselinePercent)} espera 5–10 min)`
                : `${formatPercent(data.wait5To10Percent)} espera 5–10 min (línea base: ${formatPercent(data.baselinePercent)})`
            }
            comparison={
              data.wait5To10Percent != null && (
                <BaselineComparison
                  percent={data.wait5To10Percent}
                  baseline={data.baselinePercent}
                />
              )
            }
          />
          <KpiCard
            label="Turnos reservados"
            value={formatNumber(data.reservations)}
            detail={periods.find((p) => p.value === period)?.label.toLowerCase()}
            comparison={
              <PreviousComparison
                current={data.reservations}
                previous={previous?.reservations}
                label={vs}
              />
            }
          />
          <KpiCard
            label="Check-ins realizados"
            value={data.checkInPercent == null ? NO_DATA : formatPercent(data.checkInPercent)}
            detail="de los turnos reservados"
            comparison={
              <PreviousComparison
                current={data.checkInPercent}
                previous={previous?.checkInPercent}
                points
                label={vs}
              />
            }
          />
          <KpiCard
            label="Ocupación promedio"
            value={
              data.avgOccupancy == null
                ? NO_DATA
                : `${formatNumber(data.avgOccupancy)} / ${data.capacity}`
            }
            detail="personas por franja"
            comparison={
              <PreviousComparison
                current={data.avgOccupancy}
                previous={previous?.avgOccupancy}
                label={vs}
              />
            }
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
          {nowShift && (
            <p className="admin__context">
              Ahora: <strong>{formatShift(nowShift)}</strong>
            </p>
          )}
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
            label="Edificio"
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
