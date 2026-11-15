# Runtime project storage boundary

Python and Node project launch APIs accept only canonical directories beneath PocketHost's app-private `files`, `cache`, `noBackup`, or `codeCache` directories. The check resolves symbolic links before validating containment, and the Node foreground service repeats the check when it receives its launch intent. Python multi-host startup validates every route root before importing any project.

Projects selected through PocketHost's import flow are copied into app-private storage. A project path outside those directories is rejected and must be imported again through the app. This is a filesystem-boundary hardening measure, not a process sandbox: Python and Node code still run with the PocketHost application UID and network privileges. Only owner-controlled, trusted code may be executed. Public arbitrary-code hosting remains unsupported until an OS-enforced isolation design and adversarial security review are complete.
