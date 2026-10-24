-- Fichaje: medio de pago en cada cobro. Seguro de correr dos veces.
alter table public.payments add column if not exists method text
  check (method is null or method in ('efectivo', 'transferencia', 'mercadopago'));
