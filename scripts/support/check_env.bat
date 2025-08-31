@echo off
:: Check environment variables
echo Checking environment variables...
set OPENAI_KEY_MISSING=false
set ANTHROPIC_KEY_MISSING=false

if "%OPENAI_API_KEY%"=="" (
    echo OPENAI_API_KEY environment variable is not set
    echo OpenAI models will not be available
    echo Get an API key at https://platform.openai.com/api-keys
    echo Please set it with: set OPENAI_API_KEY=your_api_key
    set OPENAI_KEY_MISSING=true
) else (
    echo OPENAI_API_KEY set: OpenAI models are available
)

if "%ANTHROPIC_API_KEY%"=="" (
    echo ANTHROPIC_API_KEY environment variable is not set
    echo Claude models will not be available
    echo Get an API key at https://www.anthropic.com/api
    echo Please set it with: set ANTHROPIC_API_KEY=your_api_key
    set ANTHROPIC_KEY_MISSING=true
) else (
    echo ANTHROPIC_API_KEY set: Claude models are available
)

if "%OPENAI_KEY_MISSING%"=="true" if "%ANTHROPIC_KEY_MISSING%"=="true" (
    echo ERROR: Both OPENAI_API_KEY and ANTHROPIC_API_KEY are missing.
    echo At least one API key is required to use language models.
    echo Please set at least one of these keys before running the application.
    exit /b 1
)