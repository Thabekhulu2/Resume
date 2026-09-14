FROM node:20-alpine
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --silent --no-progress || npm install --silent --no-progress

COPY . .

EXPOSE 4200
CMD ["npx", "ng", "serve", "--host", "0.0.0.0", "--port", "4200"]
