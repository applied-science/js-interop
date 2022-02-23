# Releases

To tag a version for release:

```
$ clj -A:release tag --patch (or --minor, --major)
```

After pushing a tagged commit, build+deploy will be triggered automatically on CircleCI.

```
git push --follow-tags
```