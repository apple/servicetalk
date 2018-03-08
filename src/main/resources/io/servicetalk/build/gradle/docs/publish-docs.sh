BRANCH_NAME=\$(git symbolic-ref -q HEAD)
BRANCH_NAME=\${BRANCH_NAME##refs/heads/}
GIT_AUTHOR=\$(git --no-pager show -s --format='%an <%ae>' HEAD)
git fetch origin +gh-pages:gh-pages
git checkout gh-pages
rm -rf docs/$version docs/current
mkdir -p docs/$version
cp -r $buildDir/asciidoc/html5/* docs/$version
cp -r docs/$version docs/current
git add docs
echo '<html><head><meta http-equiv="refresh" content="0; url=docs/current" /></head></html>' > index.html
git add index.html
rm -rf javadoc/$version javadoc/current
mkdir -p javadoc/$version
cp -r $buildDir/javadoc/* javadoc/$version
cp -r javadoc/$version javadoc/current
echo '<html><head><meta http-equiv="refresh" content="0; url=current" /></head></html>' > javadoc/index.html
git add javadoc
git commit --author="\$GIT_AUTHOR" -m "publish $version docs"
git push origin gh-pages
git checkout -f \$BRANCH_NAME
