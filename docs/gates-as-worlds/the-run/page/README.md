# The results page (https://claude.ai/artifact/M94M7K9HT5st7W8ALLCnQi)

The page is built in two steps from `../built.html` and its `built-NN.jpg` pictures:

    python3 build8.py <this folder> <the folder above> <pushed tip>   # the base page, with "No two alike"
    python3 build9.py <this folder> <pushed tip>                      # adds "Depth and air" at its head

Both write `the-run-built.html` beside themselves (not kept in the repo: it is 6 MB of inlined pictures). Publish that
file to the artifact's URL. `d-1.jpg` to `d-4.jpg` are the pictures of the depth and air section, `d-5.jpg` the two tries
at a beam of light that were taken out; each has its caption in the `.txt` beside it. `strip.py out.jpg <columns> <shots>`
lays real-game shots side by side.
