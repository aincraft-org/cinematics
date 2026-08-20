package dev.cinematics.api;

/** Who else can see an out-of-body session besides the watching player. */
public enum Audience {
  /** Props and actors are shown only to the watching player. */
  SUBJECT,
  /** Subject plus an explicit spectator list. */
  SPECTATORS,
  /** The body actually moves; other players can see the flight. */
  PUBLIC
}
