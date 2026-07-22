# Step 1: Use base image with Python 3 and Java 17 installed
FROM eclipse-temurin:17-jdk-jammy

# Step 2: Install Python dependencies
RUN apt-get update && apt-get install -y python3 python3-pip && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Step 3: Copy all files into container
COPY . .

# Step 4: Install Python packages
RUN pip3 install --no-cache-dir -r requirements.txt

# Step 5: Compile Java Order Engine
RUN javac AdvancedMatchingEngine.java

# Step 6: Expose the web port
EXPOSE 8000

# Step 7: Launch Java Engine in background (&) AND FastAPI in foreground
CMD java AdvancedMatchingEngine & uvicorn main:app --host 0.0.0.0 --port 8000