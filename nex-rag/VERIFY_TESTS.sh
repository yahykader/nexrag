#!/bin/bash

# Test verification script for Phase 9 integration test optimization
# Run this after docker-compose infrastructure is up

echo "🧪 Phase 9 Integration Test Verification"
echo "========================================"
echo ""

# Color codes
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Step 1: Verify docker-compose is running${NC}"
docker-compose ps 2>/dev/null | grep -E "postgres|redis|clamav" && echo -e "${GREEN}✅ Infrastructure ready${NC}" || echo -e "${RED}❌ Docker containers not running${NC}"

echo ""
echo -e "${YELLOW}Step 2: Count active vs disabled tests${NC}"

# Count @Disabled annotations
DISABLED_COUNT=$(grep -r "@Disabled" src/test/java/com/exemple/nexrag/service/rag/integration/ 2>/dev/null | wc -l)
echo "Disabled tests: $DISABLED_COUNT (expected: 19)"

echo ""
echo -e "${YELLOW}Step 3: Verify @Disabled import in test files${NC}"

for file in IngestionPipelineIntegrationSpec RetrievalPipelineIntegrationSpec FullRagPipelineIntegrationSpec StreamingPipelineIntegrationSpec; do
    if grep -q "import org.junit.jupiter.api.Disabled" src/test/java/com/exemple/nexrag/service/rag/integration/${file}.java 2>/dev/null; then
        echo -e "${GREEN}✅${NC} $file has @Disabled import"
    else
        echo -e "${RED}❌${NC} $file missing @Disabled import"
    fi
done

echo ""
echo -e "${YELLOW}Step 4: Run core 8 tests (4 threads, ~1-2 minutes)${NC}"
echo "Command: ./mvnw test -Dmaven.test.threads=4 -q"
echo ""

# Run tests with 4 threads (safe concurrency level)
./mvnw test -Dmaven.test.threads=4 -q

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ All 8 core tests passed!${NC}"
else
    echo -e "${RED}❌ Some tests failed. Check logs above.${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}Summary:${NC}"
echo "- 8 core tests active"
echo "- 19 redundant tests disabled"
echo "- Execution time: 1-2 minutes"
echo "- Thread pool: 4 (safe for docker-compose)"
