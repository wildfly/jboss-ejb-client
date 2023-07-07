echo "INTEGRATION TESTS"

EJB_CLIENT_REPOSITORY=$1
EJB_CLIENT_BRANCH=$2

git clone --depth=1 https://github.com/wildfly/ejb-client-testsuite

cd ejb-client-testsuite

mvn -B -ntp package -DspecificModule=prepare --batch-mode -Dejb.client.repository=https://github.com/${EJB_CLIENT_REPOSITORY} -Dejb.client.branch=${EJB_CLIENT_BRANCH}
mvn -B -ntp dependency:tree clean verify --fail-at-end --batch-mode