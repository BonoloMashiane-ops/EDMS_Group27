# Group 27 — Desktop GUI Trial and Final Submission Checklist

## Verified in this prepared package

- [x] Complete, compilable Java 17 console application.
- [x] Account registration, login and persistent password hashes.
- [x] Two-way AES-GCM-encrypted localhost chat.
- [x] Separate encrypted user history with timestamps and message IDs.
- [x] Basic validation, error messages, `/help`, `/history`, `/quit`.
- [x] Operational logs without stored plaintext chat content.
- [x] New Java Swing window with account, connection and chat screens.
- [x] GUI Send, History, Disconnect, and Sign out actions wired to EDMS services.
- [x] Swing chat layout rendered and visually reviewed with sample messages.
- [x] Automated tests: 23/23 passed with localhost socket permission.
- [x] Live Alice/Bob two-terminal messaging and clean exit demonstrated.
- [x] README and detailed user guide.
- [x] Software design and evaluation report draft.
- [x] Supplied UML and updated implementation-oriented UML.
- [x] GA2 and GA7 reflection draft; team responsibilities mapped from supplied prototype.
- [x] Test matrix, real output sample and presentation script.
- [x] Console mode retained as fallback.

## Group actions before uploading to Blackboard

- [ ] Confirm the lecturer's **current** SDN260S rubric, formatting and submission method; no current 2026 brief was attached.
- [ ] Each member verifies **their actual contribution** and revises the GA2/GA7 reflections honestly.
- [ ] Review names, student numbers, group number, submission date and any required cover page.
- [ ] Run the tests and two-terminal demo on the **actual presentation laptop**.
- [ ] Open **two Swing GUI windows** on a real Windows/NetBeans desktop and click through registration, connect, messages, history and sign out. The automated environment can render the GUI but has no desktop for an interactive click-through.
- [ ] Capture screenshots/video locally if required by the marking rubric.
- [ ] Include the single PDF report if that is specifically required, and submit the source package.
- [ ] Check academic rules on AI-assisted drafting and acknowledge assistance if required.
- [ ] Do not include live passwords, chat passphrases or personal message logs in a public submission.

## Ready-for-demo versus production readiness

**Ready for a controlled classroom localhost demo:** The console is verified; the new GUI compiles and renders, and shared protocol tests pass. A real desktop GUI rehearsal is required before presenting.  
**Ready for real-world deployment or sharing sensitive data:** No; requires authenticated peer identity, secure key exchange, rate limiting, multi-client support, stronger operational protections and independent security review.
