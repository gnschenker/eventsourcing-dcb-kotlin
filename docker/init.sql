CREATE TABLE IF NOT EXISTS events (
  position bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type text NOT NULL,
  tags text[] NOT NULL,
  data jsonb NOT NULL
);

CREATE TABLE IF NOT EXISTS event_tags (
  tag text NOT NULL,
  position bigint NOT NULL REFERENCES events(position),
  PRIMARY KEY (tag, position)
);

CREATE TABLE IF NOT EXISTS checkpoints (
  name text PRIMARY KEY,
  position bigint NOT NULL
);
