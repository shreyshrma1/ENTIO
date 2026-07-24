import { Link } from "react-router";
import { useProjects } from "../web/queries";
import StatusBadge from "../components/ui/StatusBadge";

export default function ProjectListPage() {
  const projects = useProjects();

  return (
    <main className="app-shell project-landing">
      <header className="app-header">
        <div className="landing-brand"><span className="brand-mark">E</span><span className="eyebrow">Entio</span></div>
        <h1>Ontology workbench</h1>
        <p className="muted">Choose an approved project to browse its semantic model and review controlled changes.</p>
      </header>
      <section className="content-band" aria-labelledby="projects-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Projects</p>
            <h2 id="projects-heading">Approved projects</h2>
          </div>
          {projects.isFetching && !projects.isPending ? <StatusBadge tone="neutral">Refreshing</StatusBadge> : null}
        </div>
        {projects.isPending ? <p role="status">Loading projects...</p> : null}
        {projects.isError ? <p role="alert">Could not load projects. {projects.error.message}</p> : null}
        {projects.data?.projects.length === 0 ? <p>No approved projects are configured.</p> : null}
        <div className="project-list">
          {projects.data?.projects.map((project) => (
            <Link className="project-row" key={project.id} to={`/projects/${encodeURIComponent(project.id)}`}>
              <span>
                <strong>{project.displayName}</strong>
                <small>{project.id}</small>
              </span>
              <span aria-hidden="true">Open</span>
            </Link>
          ))}
        </div>
      </section>
    </main>
  );
}
