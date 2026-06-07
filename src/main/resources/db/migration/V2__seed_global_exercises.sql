-- =============================================================================
-- V2  Seed a starter catalog of global exercises (created_by = NULL).
-- Covers every measurement_type so each code path is exercisable end-to-end.
-- Versioned (runs once), so plain INSERTs are safe.
-- =============================================================================

INSERT INTO exercises (name, category, measurement_type) VALUES
  -- weight_reps
  ('Back Squat',          'lower', 'weight_reps'),
  ('Front Squat',         'lower', 'weight_reps'),
  ('Deadlift',            'lower', 'weight_reps'),
  ('Romanian Deadlift',   'lower', 'weight_reps'),
  ('Leg Press',           'lower', 'weight_reps'),
  ('Bench Press',         'push',  'weight_reps'),
  ('Overhead Press',      'push',  'weight_reps'),
  ('Incline Bench Press', 'push',  'weight_reps'),
  ('Barbell Row',         'pull',  'weight_reps'),
  ('Lat Pulldown',        'pull',  'weight_reps'),
  ('Dumbbell Curl',       'pull',  'weight_reps'),

  -- bodyweight
  ('Pull-up',             'pull',  'bodyweight'),
  ('Chin-up',             'pull',  'bodyweight'),
  ('Push-up',             'push',  'bodyweight'),
  ('Dip',                 'push',  'bodyweight'),
  ('Bodyweight Squat',    'lower', 'bodyweight'),
  ('Sit-up',              'core',  'bodyweight'),

  -- weighted_bodyweight
  ('Weighted Pull-up',    'pull',  'weighted_bodyweight'),
  ('Weighted Dip',        'push',  'weighted_bodyweight'),

  -- duration
  ('Plank',               'core',  'duration'),
  ('Dead Hang',           'grip',  'duration'),
  ('Wall Sit',            'lower', 'duration'),

  -- distance_time
  ('Run',                 'cardio', 'distance_time'),
  ('Row',                 'cardio', 'distance_time'),
  ('Cycling',             'cardio', 'distance_time');
