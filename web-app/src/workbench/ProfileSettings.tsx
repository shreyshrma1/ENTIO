import { FormEvent, useEffect, useState } from "react";

interface ProfileSettingsProps {
  displayName: string;
  onSave: (displayName: string) => void;
}

export default function ProfileSettings({ displayName, onSave }: ProfileSettingsProps) {
  const [draftName, setDraftName] = useState(displayName);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    setDraftName(displayName);
  }, [displayName]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedName = draftName.trim();
    if (!normalizedName) {
      setMessage("Enter a display name.");
      return;
    }
    onSave(normalizedName);
    setDraftName(normalizedName);
    setMessage("Display name updated.");
  }

  return (
    <section className="content-band profile-settings" aria-labelledby="profile-settings-heading">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Account</p>
          <h2 id="profile-settings-heading">Profile</h2>
        </div>
        <span>Local browser</span>
      </div>
      <p className="muted">This name identifies you in this browser. Your development role and permissions do not change.</p>
      <form onSubmit={submit}>
        <label htmlFor="profile-display-name">Display name</label>
        <input
          id="profile-display-name"
          maxLength={80}
          value={draftName}
          onChange={(event) => {
            setDraftName(event.target.value);
            setMessage(null);
          }}
          autoComplete="name"
        />
        <button className="button primary" type="submit" disabled={!draftName.trim() || draftName.trim() === displayName}>Save name</button>
      </form>
      {message ? <p role={message === "Display name updated." ? "status" : "alert"}>{message}</p> : null}
    </section>
  );
}
