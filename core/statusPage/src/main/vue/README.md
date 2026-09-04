# Kamon Telemetry Status Page

This Vue 3 + Vite project is used to build the status page for Kamon Telemetry.
The entire status page is bundled into a single static HTML file with inlined
CSS and JS, then served by the kamon-status-page module.

Any updates to the Vue code require rebuilding by hand and copying the built
`index.html` file into the `src/main/resources/status-page/` directory. If updates
become frequent, we may consider automating this step in the future.

## Project Setup

We use Bun 1.3+ to manage dependencies. Make sure you have Bun installed on your
system. Local development might work with npm/yarn, but Bun is required for
consistent builds if we want to get something merged.

```sh
bun install
```

### Local Development

Start an application with Kamon and the Status Page module running locally, then
run the following command to start a local development server with hot-reloading:

```sh
npm run dev
```

Vite is already configured to proxy API requests to the local Kamon instance at
`http://localhost:5266`.

### Type-Check, Compile and Minify for Production

```sh
npm run build
```
