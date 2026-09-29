# Snatter website

The public site at [snatter.app](https://snatter.app): for now a single
"Coming soon" page with the app's selling points and a live render of the app.

It is plain HTML, CSS and a little JavaScript, with no build step and no
dependencies. Serve the folder from any static host.

```
cd website && python3 -m http.server 8000     # http://localhost:8000
```

| File          | Contents                                                        |
|---------------|-----------------------------------------------------------------|
| `index.html`  | The page, including the app render and the icon sprite          |
| `styles.css`  | Everything on the page, from the tokens only                    |
| `tokens.css`  | A copy of `client/web/src/tokens.css`                           |
| `script.js`   | The render's short conversation and the bot check, run once     |
| `favicon.svg` | A selected server icon with the initial S                       |

## Keeping it in step with the app

- The page follows the design system in `client/design-system`: its colours,
  type, motion and voice (sentence case, no exclamation marks, no emoji).
- `tokens.css` is a copy, so the folder deploys on its own. When a token
  changes in `client/design-system/tokens.json` and `client/web/src/tokens.css`,
  copy it here in the same commit.
- The app render is HTML drawn from the same shapes as `client/web/src/styles.css`,
  with its own `app-` classes. When the app's look changes, update the render.
- No inline styles or scripts, so the site can run under a strict
  Content-Security-Policy (`default-src 'self'`).
