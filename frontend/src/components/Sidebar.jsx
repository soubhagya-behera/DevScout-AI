function Sidebar({ tabs, active, onSelect }) {
  return (
    <nav className="sidebar" aria-label="Report sections">
      <div className="sidebar-label">Report</div>
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          className={`sidebar-item ${active === tab.id ? "active" : ""}`}
          onClick={() => onSelect(tab.id)}
        >
          <span className="sidebar-item-label">{tab.label}</span>
          {tab.count != null && (
            <span className="sidebar-count">{tab.count}</span>
          )}
        </button>
      ))}
    </nav>
  );
}

export default Sidebar;