#!/bin/bash

# BookLore Development Runner
# ===========================
# This script builds and runs BookLore for local development.
# 
# Usage:
#   ./run.sh           - Run both backend and frontend
#   ./run.sh backend   - Run only the backend (API)
#   ./run.sh frontend  - Run only the frontend (UI)
#   ./run.sh build     - Build both without running
#   ./run.sh clean     - Clean build artifacts
#   ./run.sh help      - Show this help message

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/dev-data"
BOOKS_DIR="${SCRIPT_DIR}/dev-books"
BOOKDROP_DIR="${SCRIPT_DIR}/dev-bookdrop"

BACKEND_DIR="${SCRIPT_DIR}/booklore-api"
FRONTEND_DIR="${SCRIPT_DIR}/booklore-ui"

BACKEND_PORT=8080
FRONTEND_PORT=4200

# ============================================================================
# Java Configuration (Homebrew OpenJDK 21)
# ============================================================================

setup_java_home() {
    # Check for Homebrew OpenJDK 21 installation
    # Apple Silicon (M1/M2/M3) path
    if [[ -d "/opt/homebrew/opt/openjdk@21" ]]; then
        export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
    # Intel Mac path
    elif [[ -d "/usr/local/opt/openjdk@21" ]]; then
        export JAVA_HOME="/usr/local/opt/openjdk@21"
    # Fallback: check if JAVA_HOME is already set and valid
    elif [[ -n "$JAVA_HOME" ]] && [[ -d "$JAVA_HOME" ]]; then
        : # Use existing JAVA_HOME
    else
        return 1
    fi
    
    # Add Java to PATH
    export PATH="$JAVA_HOME/bin:$PATH"
    return 0
}

# Set up Java immediately
if ! setup_java_home; then
    echo "Warning: Could not find Homebrew OpenJDK 21. Will check for system Java later."
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Process tracking
BACKEND_PID=""
FRONTEND_PID=""

# ============================================================================
# Helper Functions
# ============================================================================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_banner() {
    echo ""
    echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}                    📚 ${GREEN}BookLore Development${NC}                    ${BLUE}║${NC}"
    echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_help() {
    echo "BookLore Development Runner"
    echo ""
    echo "Usage: ./run.sh [command]"
    echo ""
    echo "Commands:"
    echo "  (none)     Run both backend and frontend (default)"
    echo "  backend    Run only the backend API server"
    echo "  frontend   Run only the frontend dev server"
    echo "  build      Build both projects without running"
    echo "  clean      Clean all build artifacts"
    echo "  help       Show this help message"
    echo ""
    echo "Ports:"
    echo "  Backend:   http://localhost:${BACKEND_PORT}"
    echo "  Frontend:  http://localhost:${FRONTEND_PORT}"
    echo ""
    echo "Data directories (created automatically):"
    echo "  SQLite DB & Config: ${DATA_DIR}"
    echo "  Books:              ${BOOKS_DIR}"
    echo "  BookDrop:           ${BOOKDROP_DIR}"
    echo ""
    echo "Prerequisites:"
    echo "  Java 21:   brew install openjdk@21"
    echo "  Node.js:   brew install node (or use nvm)"
}

# ============================================================================
# Prerequisite Checks
# ============================================================================

check_java() {
    # Try to set up Homebrew Java if not already configured
    if ! command -v java &> /dev/null; then
        setup_java_home || true
    fi
    
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed. Please install OpenJDK 21 via Homebrew:"
        log_info "  brew install openjdk@21"
        exit 1
    fi

    # Check Java version (need 21+)
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ "$JAVA_VERSION" -lt 21 ]]; then
        log_error "Java 21 or later is required. Found: Java $JAVA_VERSION"
        log_info "Install OpenJDK 21 via Homebrew: brew install openjdk@21"
        exit 1
    fi
    
    if [[ -n "$JAVA_HOME" ]]; then
        log_success "Java $JAVA_VERSION detected (JAVA_HOME: $JAVA_HOME)"
    else
        log_success "Java $JAVA_VERSION detected"
    fi
}

check_node() {
    if ! command -v node &> /dev/null; then
        log_error "Node.js is not installed. Please install Node.js 18 or later."
        log_info "Recommended: Install via nvm (https://github.com/nvm-sh/nvm)"
        exit 1
    fi

    # Check Node version (need 18+)
    NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
    if [[ "$NODE_VERSION" -lt 18 ]]; then
        log_error "Node.js 18 or later is required. Found: Node.js $NODE_VERSION"
        exit 1
    fi
    log_success "Node.js $(node -v) detected"
}

check_npm() {
    if ! command -v npm &> /dev/null; then
        log_error "npm is not installed. It should come with Node.js."
        exit 1
    fi
    log_success "npm $(npm -v) detected"
}

check_prerequisites() {
    log_info "Checking prerequisites..."
    check_java
    check_node
    check_npm
    echo ""
}

# ============================================================================
# Directory Setup
# ============================================================================

setup_directories() {
    log_info "Setting up development directories..."
    
    mkdir -p "$DATA_DIR"
    mkdir -p "$BOOKS_DIR"
    mkdir -p "$BOOKDROP_DIR"
    
    log_success "Directories created:"
    log_info "  Data:     $DATA_DIR"
    log_info "  Books:    $BOOKS_DIR"
    log_info "  BookDrop: $BOOKDROP_DIR"
    echo ""
}

# ============================================================================
# Build Functions
# ============================================================================

build_backend() {
    log_info "Building backend..."
    cd "$BACKEND_DIR"
    
    if [[ ! -f "gradlew" ]]; then
        log_error "Gradle wrapper not found in $BACKEND_DIR"
        exit 1
    fi
    
    chmod +x gradlew
    ./gradlew build -x test --quiet
    log_success "Backend built successfully"
    cd "$SCRIPT_DIR"
}

build_frontend() {
    log_info "Building frontend..."
    cd "$FRONTEND_DIR"
    
    if [[ ! -f "package.json" ]]; then
        log_error "package.json not found in $FRONTEND_DIR"
        exit 1
    fi
    
    # Install dependencies if node_modules doesn't exist or package.json is newer
    if [[ ! -d "node_modules" ]] || [[ "package.json" -nt "node_modules" ]]; then
        log_info "Installing npm dependencies (this may take a while on first run)..."
        npm install --silent
    fi
    
    log_success "Frontend dependencies ready"
    cd "$SCRIPT_DIR"
}

# ============================================================================
# Run Functions
# ============================================================================

run_backend() {
    log_info "Starting backend on port $BACKEND_PORT..."
    cd "$BACKEND_DIR"
    
    # Set environment variables for local development
    export APP_PATH_CONFIG="$DATA_DIR"
    export APP_BOOKDROP_FOLDER="$BOOKDROP_DIR"
    
    chmod +x gradlew
    ./gradlew bootRun --quiet &
    BACKEND_PID=$!
    
    cd "$SCRIPT_DIR"
    log_success "Backend starting (PID: $BACKEND_PID)"
}

run_frontend() {
    log_info "Starting frontend on port $FRONTEND_PORT..."
    cd "$FRONTEND_DIR"
    
    # Install dependencies if needed
    if [[ ! -d "node_modules" ]]; then
        log_info "Installing npm dependencies..."
        npm install --silent
    fi
    
    npm run start -- --port $FRONTEND_PORT &
    FRONTEND_PID=$!
    
    cd "$SCRIPT_DIR"
    log_success "Frontend starting (PID: $FRONTEND_PID)"
}

# ============================================================================
# Cleanup
# ============================================================================

cleanup() {
    echo ""
    log_info "Shutting down..."
    
    if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
        log_info "Stopping backend (PID: $BACKEND_PID)..."
        kill "$BACKEND_PID" 2>/dev/null || true
        wait "$BACKEND_PID" 2>/dev/null || true
    fi
    
    if [[ -n "$FRONTEND_PID" ]] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
        log_info "Stopping frontend (PID: $FRONTEND_PID)..."
        kill "$FRONTEND_PID" 2>/dev/null || true
        wait "$FRONTEND_PID" 2>/dev/null || true
    fi
    
    # Also kill any lingering gradle daemons for this project
    pkill -f "GradleDaemon.*booklore" 2>/dev/null || true
    
    log_success "Shutdown complete"
    exit 0
}

clean_build() {
    log_info "Cleaning build artifacts..."
    
    # Clean backend
    if [[ -d "$BACKEND_DIR" ]]; then
        cd "$BACKEND_DIR"
        if [[ -f "gradlew" ]]; then
            chmod +x gradlew
            ./gradlew clean --quiet
        fi
        cd "$SCRIPT_DIR"
        log_success "Backend cleaned"
    fi
    
    # Clean frontend
    if [[ -d "$FRONTEND_DIR/node_modules" ]]; then
        rm -rf "$FRONTEND_DIR/node_modules"
        log_success "Frontend node_modules removed"
    fi
    
    if [[ -d "$FRONTEND_DIR/.angular" ]]; then
        rm -rf "$FRONTEND_DIR/.angular"
        log_success "Frontend .angular cache removed"
    fi
    
    log_success "Clean complete"
}

wait_for_backend() {
    log_info "Waiting for backend to be ready..."
    local max_attempts=60
    local attempt=0
    
    while [[ $attempt -lt $max_attempts ]]; do
        if curl -s "http://localhost:${BACKEND_PORT}/api/v1/healthcheck" > /dev/null 2>&1; then
            log_success "Backend is ready!"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 1
    done
    
    log_warn "Backend may not be fully ready yet. Check logs for details."
}

# ============================================================================
# Main
# ============================================================================

trap cleanup SIGINT SIGTERM

print_banner

case "${1:-}" in
    help|--help|-h)
        print_help
        exit 0
        ;;
    
    clean)
        clean_build
        exit 0
        ;;
    
    build)
        check_prerequisites
        build_backend
        build_frontend
        log_success "Build complete!"
        exit 0
        ;;
    
    backend)
        check_java
        setup_directories
        run_backend
        echo ""
        log_info "Backend running at: ${GREEN}http://localhost:${BACKEND_PORT}${NC}"
        log_info "API docs at: ${GREEN}http://localhost:${BACKEND_PORT}/api/v1/swagger-ui.html${NC}"
        log_info "Press Ctrl+C to stop"
        echo ""
        wait $BACKEND_PID
        ;;
    
    frontend)
        check_node
        check_npm
        build_frontend
        run_frontend
        echo ""
        log_info "Frontend running at: ${GREEN}http://localhost:${FRONTEND_PORT}${NC}"
        log_info "Press Ctrl+C to stop"
        echo ""
        wait $FRONTEND_PID
        ;;
    
    "")
        # Default: run both
        check_prerequisites
        setup_directories
        
        # Build frontend dependencies first (doesn't block)
        build_frontend
        
        # Start both services
        run_backend
        run_frontend
        
        echo ""
        echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
        echo -e "  📚 BookLore is starting up!"
        echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
        echo ""
        echo -e "  ${BLUE}Backend API:${NC}  http://localhost:${BACKEND_PORT}"
        echo -e "  ${BLUE}Frontend UI:${NC}  http://localhost:${FRONTEND_PORT}"
        echo -e "  ${BLUE}API Docs:${NC}     http://localhost:${BACKEND_PORT}/api/v1/swagger-ui.html"
        echo ""
        echo -e "  ${YELLOW}Data stored in:${NC} ${DATA_DIR}"
        echo ""
        echo -e "  Press ${RED}Ctrl+C${NC} to stop all services"
        echo ""
        
        # Wait for backend and then open browser
        wait_for_backend
        
        # Wait for either process to exit
        wait
        ;;
    
    *)
        log_error "Unknown command: $1"
        echo ""
        print_help
        exit 1
        ;;
esac

