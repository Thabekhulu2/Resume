from __future__ import annotations
from pydantic import Field
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    temporal_address: str = Field("temporal:7233", env="TEMPORAL_ADDRESS")
    temporal_namespace: str = Field("default", env="TEMPORAL_NAMESPACE")
    temporal_task_queue: str = Field("main", env="TEMPORAL_TASK_QUEUE")
    supabase_url: str = Field("http://host.docker.internal:54321", env="SUPABASE_URL")
    supabase_service_role_key: str = Field("dev-service-role-key", env="SUPABASE_SERVICE_ROLE_KEY")
    anthropic_api_key: str = Field("", env="ANTHROPIC_API_KEY")
    use_local_llm: bool = Field(False, env="USE_LOCAL_LLM")
    resumes_storage_bucket: str = Field("resumes", env="RESUMES_STORAGE_BUCKET")
    http_trigger_port: int = Field(8001, env="HTTP_TRIGGER_PORT")

    class Config:
        case_sensitive = False

settings = Settings()
