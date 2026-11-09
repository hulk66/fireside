# Media in this directory is not MIT licensed

The source code in this repository is released under the MIT licence (see the
`LICENSE` file at the repository root).

The files in this directory are **not** covered by that licence. The walkthrough
video and its GIF show the author's own photographs, and those photographs remain
© Tobias Himstedt, all rights reserved. You may link to these files; you may not
reuse, redistribute or republish them as your own, or as part of your own work,
without permission.

If you fork this repository, replace these files with your own captures — the app
renders its built-in demo library with `-e demo true`, which needs no server and
no account:

```bash
adb shell am start -n org.himstedt.fireside/.MainActivity -e demo true
```
