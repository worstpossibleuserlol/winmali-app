# SDD ledger — plan: docs/superpowers/plans/2026-11-23-mali-black-screen.md

Base: `6fb14d2126c8260caea0cd2f83fe23904b442090`.

Ruling: Keep the Mali WineD3D renderer default at Vulkan — the value is present in WinMali's WineD3D UI and the bug found is in the Android Vulkan/OpenGL shared-buffer contract, not evidence that the WineD3D renderer name is invalid. Cost if wrong: the renderer default itself could still be incompatible with the bundled Wine/Mesa versions; device test will decide.

Ruling: Do not add PostHog SDK or send telemetry — the connected project's schema reports no events seen in the last 30 days and no WinMali SDK is present in this Android project. Cost if wrong: we will miss production crash evidence until the user configures the app-side SDK and consents to diagnostics.
