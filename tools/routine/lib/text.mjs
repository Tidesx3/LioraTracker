// Catalog exercises as text for an AI: one line in a search, or everything about one.

/** "Barbell Bench Press [fedb.…] weight_reps · barbell · chest (+ triceps, shoulders) · de: Bankdrücken (Langhantel)" */
export function exerciseLine(exercise) {
  const muscles = exercise.primary.join(', ') + (exercise.secondary.length ? ` (+ ${exercise.secondary.join(', ')})` : '');
  const german = exercise.names?.de && exercise.names.de !== exercise.name ? ` · de: ${exercise.names.de}` : '';
  const own = exercise.custom ? ' · the person\'s own' : '';
  return `${exercise.name} [${exercise.id}] ${exercise.tracking} · ${exercise.equipment} · ${muscles || 'no muscles listed'}${german}${own}`;
}

export function exerciseDetails(exercise) {
  const lines = [
    `${exercise.name} [${exercise.id}]`,
    `Tracking: ${exercise.tracking} · Equipment: ${exercise.equipment}${exercise.category ? ` · Category: ${exercise.category}` : ''}`,
    `Muscles: ${exercise.primary.join(', ') || '—'}${exercise.secondary.length ? `; also ${exercise.secondary.join(', ')}` : ''}`,
  ];
  const names = Object.entries(exercise.names ?? {}).filter(([language]) => language !== 'en');
  if (names.length) lines.push(`Also called: ${names.map(([language, name]) => `${name} (${language})`).join(', ')}`);
  if (exercise.aliases?.length) lines.push(`Aliases: ${exercise.aliases.join(', ')}`);
  if (exercise.rank) lines.push(`Popularity: #${exercise.rank}`);
  if (exercise.custom) lines.push("The person's own exercise, from their backup.");
  if (exercise.instructions?.length) lines.push('How:', ...exercise.instructions.map((step, i) => `  ${i + 1}. ${step}`));
  return lines.join('\n');
}
