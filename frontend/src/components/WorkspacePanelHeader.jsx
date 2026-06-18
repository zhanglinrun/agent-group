export function WorkspacePanelHeader({
  className,
  title,
  subtitle,
  subtitleElement,
  eyebrow,
  trailing
}) {
  return (
    <div className={className}>
      <div>
        {eyebrow}
        <strong>{title}</strong>
        {subtitleElement || (subtitle ? <span>{subtitle}</span> : null)}
      </div>
      {trailing}
    </div>
  );
}
